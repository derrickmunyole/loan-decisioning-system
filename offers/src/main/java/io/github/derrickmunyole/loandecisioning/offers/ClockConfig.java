package io.github.derrickmunyole.loandecisioning.offers;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The first module in this codebase to need injectable time — {@link Offer#accept} and {@link
 * OfferExpiryJob} both need to move "now" in a test without sleeping. Scoped to {@code offers}
 * rather than {@code platform-common}: only one module wants this today, so promoting it to the
 * shared kernel before a second real caller exists would be the same premature generalization the
 * "extract a port on the second real caller" pattern avoids elsewhere in this codebase.
 */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
