package audio.recorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"audio"})
public class AudioRecorderLiveAnguageDetectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(AudioRecorderLiveAnguageDetectorApplication.class, args);
	}

}
