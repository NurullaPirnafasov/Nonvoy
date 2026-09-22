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
        }
        User created = new User();
        created.setTelegramId(telegramId);
        created.setName(name);
        return userRepository.save(created);
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
    public void saveDraftProduct(User user, Long productId) {
        user.setDraftProductId(productId);
        user.setState(UserState.WAITING_QUANTITY);
        userRepository.save(user);
    }

    @Override
    public void saveReceipt(User user, String receiptFileId) {
        user.setDraftReceiptFileId(receiptFileId);
        user.setState(UserState.FINAL_CONFIRM);
        userRepository.save(user);
    }

    @Override
    public void resetToIdle(User user) {
        user.setState(UserState.IDLE);
        user.setDraftProductId(null);
        user.setDraftReceiptFileId(null);
        userRepository.save(user);
    }
}
