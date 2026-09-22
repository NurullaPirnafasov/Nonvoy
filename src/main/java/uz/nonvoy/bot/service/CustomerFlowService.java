package uz.nonvoy.bot.service;

import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

public interface CustomerFlowService {
    List<PartialBotApiMethod<?>> handleMessage(Update update);

    List<PartialBotApiMethod<?>> handleCallback(Update update);
}
