package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.UserState;

public interface UserService {
    User findOrCreate(Long telegramId, String name);

    User savePhone(User user, String phone);

    User updateState(User user, UserState newState);
}
