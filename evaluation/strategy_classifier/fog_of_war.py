import numpy as np


def generate_scouting_mask(total_seconds: int, rng: np.random.Generator) -> np.ndarray:
    mask = np.zeros(total_seconds, dtype=np.float32)

    scout_arrival = int(rng.integers(90, 150))
    if scout_arrival >= total_seconds:
        return mask

    first_duration = int(rng.integers(15, 30))
    first_end = min(scout_arrival + first_duration, total_seconds)
    mask[scout_arrival:first_end] = 1.0

    t = first_end + int(rng.integers(40, 80))
    while t < total_seconds:
        duration = int(rng.integers(10, 25))
        end = min(t + duration, total_seconds)
        mask[t:end] = 1.0
        t = end + int(rng.integers(40, 80))

    return mask
