package org.info.tg_bot.bot.constants;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Commands {
    public static final String START = "/start";
    public static final String UPLOAD = "/upload";
    public static final String STATUS = "/status";
    public static final String HELP = "/help";

    public List<String> getAllCommands(){
        return new ArrayList<>(){};
    }
}
