package com.jobtrackr;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/greet")
    public String say() {
        return "Hello JobTrackr!";
    }

    @GetMapping("/welcome")
    public String come() {
        return "Welcome to JobTrackr!";
    }

    @GetMapping("/application/{id}")
    public String getId(@PathVariable int id) {
        return "Application Id :" + id;
    }

    @GetMapping("/hello/{name}")
    public String helloUser(@PathVariable String name) {
        return "Hello " + name + "!";
    }

    @GetMapping("/hey")
    public String heyUser(@RequestParam String name, @RequestParam(defaultValue = "") String city) {
        if(city.isEmpty()) {
            return "Hello " + name;
        } else{
            return "Hello " + name + " & Your City is " + city + "!";
        }
    }

//    @PostMapping("/greet")
//    public String greet(@RequestBody Map<String, String> body){
//       String name = body.get("name");
//       return "Hello " + name + "!" + " - " +name.length();
//    }

//    @PostMapping("/greet")
//    public String greet(@RequestBody GreetingRequest greetingRequest){
//        String name = greetingRequest.getName();
//        if(name == null) {
//            return "No Name";
//        } else{
//            return "Hello " + name + "!";
//        }
//    }

    @PostMapping("/greet")
    public ResponseEntity<String> greet(@Valid @RequestBody GreetingRequest greetingRequest) {
        String name =  greetingRequest.getName();
//        if(name == null || name.isBlank()) {
//            return ResponseEntity.badRequest().body("Name is required");
//        } else {
//            return ResponseEntity.ok().body("Hello " + name + "!");
//        }
        return ResponseEntity.ok().body("Hello " + name + "!");
    }
}