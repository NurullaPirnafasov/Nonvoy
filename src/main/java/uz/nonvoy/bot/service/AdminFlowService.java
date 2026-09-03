package uz.nonvoy.bot.service;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

public interface AdminFlowService {
    List<BotApiMethod<?>> handleCallback(Update update);

    List<BotApiMethod<?>> handleMessage(Update update);
}
