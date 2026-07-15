package net.ddns.adambravo79.tmill.telegram.matcher;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Update;

import io.ksilisk.telegrambot.core.matcher.Matcher;

@Component
public class AllUpdateMatcher implements Matcher<Update> {
    @Override
    public boolean match(Update update) {
        return true;
    }
}
