package ant.gasmeter;

import java.time.LocalDateTime;

public record DataPoint(LocalDateTime timestamp, int value) {
}
