package alg;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
class Person {
    private String name;
    private String phoneNumber;
     // getters and setters
}

public class test {
    public static void main(String[] args) {
        List<Person> bookList = new ArrayList<>();
        bookList.add(new Person("jack","18163138123"));
        bookList.add(new Person("martin",null));
        // 空指针异常
        bookList.stream().collect(Collectors.toMap(Person::getName, Person::getPhoneNumber));
    }
}
