package uz.nonvoy.bot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface CustomerFlowService {
    public SendMessage handleUpdate(Update update);
}
