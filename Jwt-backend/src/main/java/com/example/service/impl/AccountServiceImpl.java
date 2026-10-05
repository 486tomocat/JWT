package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.Account;
import com.example.entity.dto.AccountDetails;
import com.example.entity.vo.request.AccountSaveVO;
import com.example.entity.vo.request.ChangePasswordVO;
import com.example.entity.vo.request.ConfirmResetVO;
import com.example.entity.vo.request.ModifyEmailVO;
import com.example.entity.vo.request.EmailRegisterVO;
import com.example.entity.vo.request.EmailResetVO;
import com.example.mapper.AccountDetailsMapper;
import com.example.mapper.AccountMapper;
import com.example.service.AccountService;
import com.example.service.EmailService;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    @Resource
    EmailService emailService;

    /**
     * 这里直接注入 Mapper 而不是 AccountDetailsService：
     * AccountDetailsServiceImpl 反过来依赖 AccountService，注 Service 会形成循环依赖，
     * Spring Boot 默认禁止循环引用，启动就会失败。
     */
    @Resource
    AccountDetailsMapper accountDetailsMapper;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    FlowUtils utils;

    @Resource
    PasswordEncoder encoder;

    /** JWT 有效期（单位：天）。封禁标记的 TTL 必须和它对齐，见 handleBanned */
    @Value("${spring.security.jwt.expire}")
    int expire;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = this.findAccountByNameOrEmail(username);
        if (account == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        String role = account.getRole();
        if (role == null || role.isBlank()) {
            role = Const.ROLE_DEFAULT;
        }

        return User
                .withUsername(username)
                .password(account.getPassword())
                .roles(role)
                .build();
    }

    @Override
    public String registerEmailVerifyCode(String type, String email, String ip) {
        synchronized (ip.intern()) {
            if (!this.verifyLimit(ip))
                return "请求频繁，请稍后再试";
            Random random = new Random();
            int code = random.nextInt(899999) + 100000;
            // 阶段 3 改造：不再直接往 MQ 丢一个 Map，而是交给 EmailService ——
            // 它先把「邮件记录」落库（status=发送中），再投递到带死信交换机的 mail 队列，
            // 这样邮件发没发出去、失败了几次，管理后台都能查到。
            emailService.sendVerifyEmail(type, email, code);
            stringRedisTemplate.opsForValue()
                    .set(Const.VERIFY_EMAIL_DATA + email, String.valueOf(code), 3, TimeUnit.MINUTES);
            return null;
        }
    }

    @Override
    public String registerEmailAccount(@Valid EmailRegisterVO vo) {
        String email = vo.getEmail();
        String username = vo.getUsername();
        String key = Const.VERIFY_EMAIL_DATA + email;
        String code = stringRedisTemplate.opsForValue().get(key);
        if (code == null) return "请先获取验证码";
        if (!code.equals(vo.getCode())) return "验证码输入错误，请重新输入";
        if (this.existAccountByEmail(email)) return "此电子邮件已被其他用户注册";
        if (this.existAccountByUsername(username)) return "用户名已被注册，请重新输入";
        String password = encoder.encode(vo.getPassword());
        // 用 setter 逐项赋值，而不是 9 个位置参数的构造器：
        // 字段一多，多传/错位一个参数编译器不会报错，但会埋下诡异 bug。
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(password);
        account.setEmail(email);
        account.setRole(Const.ROLE_DEFAULT);
        account.setRegisterTime(new Date());
        if (this.save(account)) {
            stringRedisTemplate.delete(key);
            return null;
        } else {
            return "内部错误，请联系管理员";
        }
    }

    @Override
    public String resetEmailAccountPassword(EmailResetVO vo) {
        String email = vo.getEmail();
        String verify = this.resetConfirm(new ConfirmResetVO(email, vo.getCode()));
        if (verify != null) return verify;
        String password = encoder.encode(vo.getPassword());
        boolean update = this.update().eq("email", email).set("password", password).update();
        if (update) {
            stringRedisTemplate.delete(Const.VERIFY_EMAIL_DATA + email);
        }
        return null;
    }

    @Override
    public String resetConfirm(ConfirmResetVO vo) {
        String email = vo.getEmail();
        String code = stringRedisTemplate.opsForValue().get(Const.VERIFY_EMAIL_DATA + email);
        if (code == null) return "请先获取验证码";
        if (!code.equals(vo.getCode())) return "验证码错误，请重新输入";
        return null;
    }

    @Override
    public String saveAccountByAdmin(AccountSaveVO vo) {
        Account old = this.findAccountById(vo.getId());
        if (old == null) return "用户不存在";
        String username = vo.getUsername();
        if (username != null && !username.equals(old.getUsername()) && this.existAccountByUsername(username)) {
            return "用户名已被其他用户使用";
        }
        Account update = new Account();
        update.setId(vo.getId());
        update.setUsername(username);
        update.setEmail(vo.getEmail());
        update.setRole(vo.getRole());
        update.setMute(vo.isMute());
        update.setBanned(vo.isBanned());
        // updateById 默认忽略 null 字段（字段策略 NOT_NULL），
        // 所以 password / registerTime / avatar 不会因为这里没赋值而被清空。
        this.updateById(update);
        // 封禁状态变化时同步 Redis 标记，让封禁/解封「立即生效」
        this.handleBanned(old, vo.isBanned());
        if (vo.getDetail() != null) {
            AccountDetails details = vo.getDetail();
            details.setId(vo.getId());
            // db_account_details 的主键就是用户 id，且不一定是自增：
            // 不存在就 insert，存在就 update，不能用 saveOrUpdate 的默认判断。
            if (accountDetailsMapper.selectById(vo.getId()) == null) {
                accountDetailsMapper.insert(details);
            } else {
                accountDetailsMapper.updateById(details);
            }
        }
        return null;
    }

    @Override
    public void modifyPassword(int id, String newPassword) {
        this.update().eq("id", id).set("password", encoder.encode(newPassword)).update();
    }

    @Override
    public String modifyEmail(int id, ModifyEmailVO vo) {
        String email = vo.getEmail();
        // 验证码是发到「新邮箱」的：能收到就证明用户确实拥有它
        String key = Const.VERIFY_EMAIL_DATA + email;
        String code = stringRedisTemplate.opsForValue().get(key);
        if (code == null) return "请先获取验证码";
        if (!code.equals(vo.getCode())) return "验证码错误，请重新输入";

        // 邮箱是登录凭据之一，不能和别人的账号重复
        Account bound = this.findAccountByNameOrEmail(email);
        if (bound != null && !bound.getId().equals(id)) {
            return "该邮箱已被其他账号绑定";
        }

        boolean update = this.update().eq("id", id).set("email", email).update();
        if (update) {
            // 用过即焚，避免同一条验证码被重复利用
            stringRedisTemplate.delete(key);
        }
        return update ? null : "内部错误，请联系管理员";
    }

    @Override
    public String changePassword(int id, ChangePasswordVO vo) {
        Account account = this.findAccountById(id);
        if (account == null) return "用户不存在";
        // 必须校验原密码：否则只要令牌泄露就能直接把密码改掉、把号占为己有
        if (!encoder.matches(vo.getPassword(), account.getPassword())) {
            return "原密码错误，请重新输入";
        }
        boolean update = this.update()
                .eq("id", id)
                .set("password", encoder.encode(vo.getNewPassword()))
                .update();
        return update ? null : "内部错误，请联系管理员";
    }

    public Account findAccountByNameOrEmail(String text) {
        return this.query()
                .eq("username", text).or()
                .eq("email", text)
                .one();
    }

    @Override
    public Account findAccountById(int id) {
        return this.query().eq("id", id).one();
    }

    private boolean existAccountByEmail(String email) {
        return this.baseMapper.exists(Wrappers.<Account>query().eq("email", email));
    }

    private boolean existAccountByUsername(String username) {
        return this.baseMapper.exists(Wrappers.<Account>query().eq("username", username));
    }

    /**
     * 同步 Redis 里的封禁标记。
     *
     * 关键点：TTL 用「天」而不是参考实现的「小时」。
     * 参考实现写的是 TimeUnit.HOURS，而 expire 配的是 7（天），
     * 于是封禁 7 小时后就失效，但用户手里那张令牌还能再用 6 天多——封禁形同虚设。
     */
    private void handleBanned(Account old, boolean banned) {
        String key = Const.BANNED_BLOCK + old.getId();
        if (!old.isBanned() && banned) {
            stringRedisTemplate.opsForValue().set(key, "true", expire, TimeUnit.DAYS);
        } else if (old.isBanned() && !banned) {
            stringRedisTemplate.delete(key);
        }
    }

    /**
     * 按 IP 限制验证码发送频率。
     * 修复点：原来这里用了 VERIFY_EMAIL_DATA（验证码本身）当前缀，
     * 会和验证码共用一个 key 空间，改成专门的 VERIFY_EMAIL_LIMIT。
     */
    private boolean verifyLimit(String ip) {
        String key = Const.VERIFY_EMAIL_LIMIT + ip;
        return utils.limitOnceCheck(key, 60);
    }
}
