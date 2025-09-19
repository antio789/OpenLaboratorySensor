package ant.gasmeter.utils;

import java.time.LocalDateTime;

public record DataPoint(LocalDateTime timestamp, int value) {
}
