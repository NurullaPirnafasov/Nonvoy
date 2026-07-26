package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.UserState;

public interface UserService {
    User findOrCreate(Long telegramId, String name);

    void savePhone(User user, String phone);

    void updateState(User user, UserState newState);

    void saveQuantity(int quantity, User user);

    void resetToIdle(User user);
}
