package com.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.AccountPrivacy;
import com.example.entity.vo.request.PrivacySaveVO;
import com.example.mapper.AccountPrivacyMapper;
import com.example.service.AccountPrivacyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AccountPrivacyServiceImpl extends ServiceImpl<AccountPrivacyMapper, AccountPrivacy>
        implements AccountPrivacyService {

    @Override
    public AccountPrivacy accountPrivacy(int id) {
        // 没有记录 = 从没设置过 = 全部公开
        return Optional.ofNullable(this.getById(id)).orElse(new AccountPrivacy(id));
    }

    @Override
    @Transactional
    public void savePrivacy(int id, PrivacySaveVO vo) {
        AccountPrivacy privacy = this.accountPrivacy(id);
        boolean status = vo.isStatus();
        switch (vo.getType()) {
            case "phone" -> privacy.setPhone(status);
            case "email" -> privacy.setEmail(status);
            case "wx" -> privacy.setWx(status);
            case "qq" -> privacy.setQq(status);
            case "gender" -> privacy.setGender(status);
            // 未知字段直接忽略：@Pattern 已经拦了一道，这里再兜一次，别把脏数据写进库
            default -> {
                return;
            }
        }
        this.saveOrUpdate(privacy);
    }
}
