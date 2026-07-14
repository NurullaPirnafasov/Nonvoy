package uz.nonvoy.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    User findByTelegramId(Long telegramId);
}
