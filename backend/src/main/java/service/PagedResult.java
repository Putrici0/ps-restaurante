package service;

import java.util.List;

public record PagedResult<T>(List<T> items, String nextCursor, int limit) {
}
