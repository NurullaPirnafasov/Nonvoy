package uz.nonvoy.bot.service;

import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

public interface AdminFlowService {
    List<PartialBotApiMethod<?>> handleCallback(Update update);

    List<PartialBotApiMethod<?>> handleMessage(Update update);
}
