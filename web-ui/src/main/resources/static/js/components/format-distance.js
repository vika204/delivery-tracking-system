export function formatDistance(km) {
    if (km == null) return '—';
    const distance = Number(km);
    return distance > 0 && distance < 1
        ? `${Math.round(distance * 1000)} m`
        : `${distance} km`;
}
