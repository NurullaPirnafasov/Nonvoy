package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.UserState;
import uz.nonvoy.bot.repository.UserRepository;
import uz.nonvoy.bot.service.UserService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    public User findOrCreate(Long telegramId, String name) {
        Optional<User> user = userRepository.findByTelegramId(telegramId);
        if (user.isPresent()) {
            return user.get();
        } else {
            User u = new User();
            u.setTelegramId(telegramId);
            u.setName(name);
            return userRepository.save(u);
        }
    }

    @Override
    public void savePhone(User user, String phone) {
        user.setPhone(phone);
        user.setState(UserState.IDLE);
        userRepository.save(user);
    }

    @Override
    public void updateState(User user, UserState newState) {
        user.setState(newState);
        userRepository.save(user);
    }

    @Override
    public void saveQuantity(int quantity, User user) {
        user.setDraftQuantity(quantity);
        user.setState(UserState.CONFIRMING);
        userRepository.save(user);
    }

    @Override
    public void resetToIdle(User user) {
        user.setState(UserState.IDLE);
        user.setDraftQuantity(null);
        userRepository.save(user);
    }
}
