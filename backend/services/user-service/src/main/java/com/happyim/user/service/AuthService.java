package com.happyim.user.service;

import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.dto.*;
import com.happyim.common.model.entity.User;
import com.happyim.common.service.IdGenerator;
import com.happyim.common.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String REFRESH_TOKEN_PREFIX = "refresh:token:";
    private static final String REFRESH_USER_PREFIX = "refresh:user:";
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final long CODE_TTL_SECONDS = 300;
    private static final long SEND_CD_SECONDS = 60;

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final MailService mailService;
    private final IdGenerator idGenerator;
    private final RedisTemplate<String, String> redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil, MailService mailService,
                       IdGenerator idGenerator, RedisTemplate<String, String> redisTemplate) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.mailService = mailService;
        this.idGenerator = idGenerator;
        this.redisTemplate = redisTemplate;
    }

    // ==================== 发送验证码 ====================

    public void sendCode(String email, String type) {
        String cdKey = codeSendCdKey(email, type);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cdKey))) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION, "发送过于频繁，请60秒后再试");
        }

        String code = String.format("%06d", new Random().nextInt(1000000));
        redisTemplate.opsForValue().set(codeKey(email, type), code, Duration.ofSeconds(CODE_TTL_SECONDS));
        redisTemplate.opsForValue().set(cdKey, "1", Duration.ofSeconds(SEND_CD_SECONDS));

        mailService.sendVerificationCode(email, code);
    }

    // ==================== 注册 ====================

    @Transactional
    public RegisterResult register(RegisterRequest req) {
        verifyCode(req.getEmail(), req.getCode(), "register");

        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw new BizException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userMapper.findByEmail(req.getEmail()) != null) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = new User();
        user.setId(idGenerator.nextUserId());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setAvatarUrl(req.getAvatarUrl() != null && !req.getAvatarUrl().isBlank()
                ? req.getAvatarUrl() : randomDefaultAvatar());
        user.setEmailVerified(1);
        user.setStatus(1);

        userMapper.insert(user);
        redisTemplate.delete(codeKey(req.getEmail(), "register"));

        log.info("用户注册成功: id={}, username={}, email={}", user.getId(), user.getUsername(), user.getEmail());
        return new RegisterResult(user.getId(), user.getUsername(), user.getEmail(), user.getNickname());
    }

    // ==================== 登录 ====================

    public TokenResponse login(LoginRequest req, String ip) {
        verifyCode(req.getEmail(), req.getCode(), "login");

        User user = userMapper.findByEmail(req.getEmail());
        if (user == null) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (user.getStatus() == 0) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getEmailVerified() == 0) {
            throw new BizException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        String jti = jwtUtil.getJti(refreshToken);
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("user_id", String.valueOf(user.getId()));
        sessionData.put("device", "web");
        sessionData.put("ip", ip);
        redisTemplate.opsForHash().putAll(REFRESH_TOKEN_PREFIX + jti, sessionData);
        redisTemplate.expire(REFRESH_TOKEN_PREFIX + jti, Duration.ofSeconds(jwtUtil.getRefreshTokenExpire()));
        redisTemplate.opsForSet().add(REFRESH_USER_PREFIX + user.getId(), jti);
        redisTemplate.expire(REFRESH_USER_PREFIX + user.getId(), Duration.ofSeconds(jwtUtil.getRefreshTokenExpire()));

        userMapper.updateLoginInfo(user.getId(), ip);
        redisTemplate.delete(codeKey(req.getEmail(), "login"));

        log.info("用户登录成功: id={}, email={}", user.getId(), user.getEmail());
        return buildTokenResponse(user, accessToken, refreshToken);
    }

    // ==================== 刷新 Token ====================

    public TokenResponse refreshToken(String refreshTokenStr) {
        String jti;
        Long userId;
        try {
            jti = jwtUtil.getJti(refreshTokenStr);
            userId = jwtUtil.getUserId(refreshTokenStr);
        } catch (Exception e) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (Boolean.FALSE.equals(redisTemplate.hasKey(REFRESH_TOKEN_PREFIX + jti))) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String oldKey = REFRESH_TOKEN_PREFIX + jti;
        Map<Object, Object> oldSession = redisTemplate.opsForHash().entries(oldKey);

        redisTemplate.delete(oldKey);
        redisTemplate.opsForSet().remove(REFRESH_USER_PREFIX + userId, jti);

        String newAccessToken = jwtUtil.generateAccessToken(userId);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);

        String newJti = jwtUtil.getJti(newRefreshToken);
        Map<String, String> newSession = new HashMap<>();
        newSession.put("user_id", String.valueOf(userId));
        newSession.put("device", String.valueOf(oldSession.getOrDefault("device", "web")));
        newSession.put("ip", String.valueOf(oldSession.getOrDefault("ip", "")));
        redisTemplate.opsForHash().putAll(REFRESH_TOKEN_PREFIX + newJti, newSession);
        redisTemplate.expire(REFRESH_TOKEN_PREFIX + newJti, Duration.ofSeconds(jwtUtil.getRefreshTokenExpire()));
        redisTemplate.opsForSet().add(REFRESH_USER_PREFIX + userId, newJti);

        User user = userMapper.findById(userId);
        return buildTokenResponse(user, newAccessToken, newRefreshToken);
    }

    // ==================== 登出 ====================

    public void logout(String accessToken, String refreshTokenStr) {
        String accessJti = jwtUtil.getJti(accessToken);
        long remaining = jwtUtil.getRemainingSeconds(accessToken);
        if (remaining > 0) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + accessJti, "1", Duration.ofSeconds(remaining));
        }

        String refreshJti = jwtUtil.getJti(refreshTokenStr);
        Long userId = jwtUtil.getUserId(refreshTokenStr);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshJti);
        redisTemplate.opsForSet().remove(REFRESH_USER_PREFIX + userId, refreshJti);

        log.info("用户登出: userId={}", userId);
    }

    // ==================== 忘记密码 / 重置密码 ====================

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        verifyCode(req.getEmail(), req.getCode(), "reset-password");

        User user = userMapper.findByEmail(req.getEmail());
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        String newHash = passwordEncoder.encode(req.getNewPassword());
        userMapper.updatePassword(user.getId(), newHash);

        // 清除该用户所有 refresh_token，强制重新登录
        String userKey = REFRESH_USER_PREFIX + user.getId();
        java.util.Set<String> jtis = redisTemplate.opsForSet().members(userKey);
        if (jtis != null) {
            for (String jti : jtis) {
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + jti);
            }
        }
        redisTemplate.delete(userKey);
        redisTemplate.delete(codeKey(req.getEmail(), "reset-password"));

        log.info("密码重置成功: userId={}", user.getId());
    }

    // ==================== 黑名单检查 ====================

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    // ==================== 内部方法 ====================

    private void verifyCode(String email, String code, String type) {
        String key = codeKey(email, type);
        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null) {
            throw new BizException(ErrorCode.CODE_ERROR);
        }
        if (!storedCode.equals(code)) {
            throw new BizException(ErrorCode.CODE_ERROR);
        }
    }

    private TokenResponse buildTokenResponse(User user, String accessToken, String refreshToken) {
        TokenResponse resp = new TokenResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setEmail(user.getEmail());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        resp.setAccessTokenExpiresIn(jwtUtil.getAccessTokenExpire());
        resp.setRefreshTokenExpiresIn(jwtUtil.getRefreshTokenExpire());
        return resp;
    }

    // Redis key helpers
    private String codeKey(String email, String type) {
        return "email:code:" + type + ":" + email;
    }

    private String codeSendCdKey(String email, String type) {
        return "email:send:cd:" + type + ":" + email;
    }

    private String randomDefaultAvatar() {
        String[] avatars = {"vibrent_2.png", "vibrent_3.png", "vibrent_24.png",
                "bluey_2.png", "bluey_5.png", "upstream_5.png", "upstream_13.png"};
        return avatars[new Random().nextInt(avatars.length)];
    }
}
