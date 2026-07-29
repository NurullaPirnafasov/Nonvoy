package uz.nonvoy.bot.service.impl;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.nonvoy.bot.service.AdminFlowService;

import java.util.List;

@Service
public class AdminFlowServiceImpl implements AdminFlowService {

    @Override
    public List<BotApiMethod<?>> handleCallback(Update update) {
        return null;
    }

    @Override
    public List<BotApiMethod<?>> handleMessage(Update update) {
        return null;
    }
}
