package org.info.tg_bot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.service.UserService;
import org.info.tg_bot.util.EncryptionUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev")
public class DevController {
    private final UserService userService;
    private final EncryptionUtil encryptionUtil;

    //curl http://localhost:6969/dev/users
    @GetMapping("/users")
    public ResponseEntity<String> getAllUsernames(){
        log.info("Getting all users...");
        return ResponseEntity.ok(userService.getAllUsernames());
    }

    //curl -X DELETE "http://localhost:6969/dev/invalid?name=name&surname=surname&fathername=fathename" \
    //     -H "Content-Type: application/json" \
    //     -v
    @DeleteMapping("/invalid")
    public ResponseEntity<String> deleteInvalidUserByName(@RequestParam String name,
                                                          @RequestParam String surname,
                                                          @RequestParam(required = false) String fathername) throws Exception {
        return ResponseEntity.ok(userService.deleteUserByName(name, surname, fathername));
    }

    @PostMapping("/decode")
    public ResponseEntity<String> decode(@RequestBody String string) throws Exception {
        return ResponseEntity.ok(encryptionUtil.decrypt(string));
    }

    @PostMapping("/encode")
    public ResponseEntity<String> encode(@RequestBody String string) throws Exception {
        return ResponseEntity.ok(encryptionUtil.encrypt(string));
    }
}

