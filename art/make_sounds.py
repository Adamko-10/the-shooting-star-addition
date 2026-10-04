# Synthesises SS-05 Halley's sounds (mono, 44.1 kHz Ogg Vorbis, peaks and loudness matched to the remote's own).
# Run: python3 make_sounds.py [resources dir]. Each sound is one function below; tweak and re-run.
import os
import sys
import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
OUT = (sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')) + '/assets/shooting_star_addition/sounds'
rng = np.random.default_rng(505)


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def noise(seconds):
    return rng.standard_normal(int(SR * seconds))


def brown(seconds):
    n = np.cumsum(rng.standard_normal(int(SR * seconds)))
    n = signal.lfilter([1, -1], [1, -0.995], n)  # leaky: keeps it from drifting
    return n / (np.abs(n).max() + 1e-9)


def lowpass(x, hz, order=4):
    b, a = signal.butter(order, min(hz, SR / 2 - 100) / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def highpass(x, hz, order=4):
    b, a = signal.butter(order, hz / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def bandpass(x, lo, hi, order=3):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def sweep_lowpass(x, start_hz, end_hz, curve=1.0, block=512):
    """A low-pass whose cutoff glides from start to end (block-wise, state carried across blocks)."""
    out = np.zeros_like(x)
    zi = None
    n = len(x)
    for i in range(0, n, block):
        k = (i / n) ** curve
        hz = start_hz * (end_hz / start_hz) ** k
        b, a = signal.butter(2, min(hz, SR / 2 - 200) / (SR / 2), 'low')
        if zi is None:
            zi = signal.lfilter_zi(b, a) * 0.0
        out[i:i + block], zi = signal.lfilter(b, a, x[i:i + block], zi=zi)
    return out


def env(t, attack, decay_from, decay_tau):
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    d = np.where(t > decay_from, np.exp(-(t - decay_from) / decay_tau), 1.0)
    return a * d


def chirp(t, f0, f1, curve='exp'):
    if curve == 'exp':
        f = f0 * (f1 / f0) ** (t / t[-1])
    else:
        f = f0 + (f1 - f0) * t / t[-1]
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def bell(t, f, decay, ratio=1.4, index=2.0):
    """A small FM bell: bright at the strike, mellowing as it rings."""
    i = index * np.exp(-t / (decay * 0.3))
    return np.sin(2 * np.pi * f * t + i * np.sin(2 * np.pi * f * ratio * t)) * np.exp(-t / decay)


def place(buffer, sound, at):
    sound = np.array(sound, dtype=float)
    edge = min(len(sound) // 4, int(SR * 0.004))
    if edge > 1:
        # never start or stop on a click
        sound[:edge] *= np.linspace(0, 1, edge)
        sound[-edge:] *= np.linspace(1, 0, edge)
    start = int(at * SR)
    end = min(len(buffer), start + len(sound))
    if end > start:
        buffer[start:end] += sound[:end - start]


def reverb(x, seconds=1.6, mix=0.25):
    """A cheap, dense tail: noise impulse response shaped to decay."""
    tt = t_axis(seconds)
    ir = rng.standard_normal(len(tt)) * np.exp(-tt / (seconds / 5.0)) * np.cos(np.pi * 0.5 * tt / seconds)
    ir = lowpass(ir, 6000)
    dry = np.concatenate([x, np.zeros(len(tt))])
    wet = np.zeros(len(dry))
    conv = signal.fftconvolve(x, ir)
    wet[:min(len(dry), len(conv))] = conv[:len(dry)]
    wet /= np.abs(wet).max() + 1e-9
    return dry * (1 - mix) + wet * mix * np.abs(x).max()


def finish(name, x, peak=0.9, fade_out=0.05, drive=0.0):
    x = np.asarray(x, dtype=float)
    x -= np.mean(x)
    if drive > 0.0:
        # soft saturation: tames the transient so the body of the sound can be as loud as the remote's own
        x = np.tanh(x / (np.abs(x).max() + 1e-9) * drive) / np.tanh(drive)
    f = int(SR * fade_out)
    if f > 0:
        x[-f:] *= np.linspace(1, 0, f)
    x *= peak / (np.abs(x).max() + 1e-9)
    os.makedirs(OUT, exist_ok=True)
    sf.write(os.path.join(OUT, name + '.ogg'), x.astype(np.float32), SR, format='OGG', subtype='VORBIS')
    w = int(SR * 0.4)
    loud = np.sqrt(np.convolve(x ** 2, np.ones(w) / w, mode='valid').max()) if len(x) > w else 0
    print(f'{name:18s} {len(x) / SR:5.2f}s  rms {np.sqrt(np.mean(x ** 2)):.3f}  loudest 0.4s {loud:.3f}')


# ---- The mark lands: a digital zap down onto the land, a cold chime, a shimmering tail. -----------------------------
def mark():
    total = t_axis(2.4)
    out = np.zeros(len(total))
    tz = t_axis(0.28)
    zap = chirp(tz, 3200, 520) * np.exp(-tz / 0.09) * 0.6
    place(out, zap, 0.0)
    tb = t_axis(2.36)
    chime = (bell(tb, 1318.5, 0.9) * 0.55 + bell(tb, 1975.5, 0.7, ratio=2.0) * 0.4 + bell(tb, 2637.0, 0.5, ratio=1.5) * 0.25)
    place(out, chime, 0.04)
    tt = t_axis(0.25)
    thump = np.sin(2 * np.pi * 62 * tt) * np.exp(-tt / 0.07) * 0.7
    place(out, thump, 0.02)
    shimmer = bandpass(noise(2.4), 4000, 9000) * env(total, 0.05, 0.2, 0.6) * 0.18
    out += shimmer
    finish('halley_mark', reverb(out, 1.2, 0.2)[:int(SR * 2.4)], fade_out=0.3)


# ---- The countdown to touchdown: pings closing in faster and faster over a rising cold drone. ----------------------
def countdown():
    seconds = 13.55  # COUNTDOWN (t=29) to TOUCHDOWN (t=300)
    total = t_axis(seconds)
    out = np.zeros(len(total))
    # pings: the gap shrinks from 1.0 s toward 0.09 s, so they run together into a trill at the end
    at, gap = 0.0, 1.0
    while at < seconds - 0.05:
        k = at / seconds
        tp = t_axis(0.12)
        pitch = 1567.98 * (1.0 + 0.25 * k)
        ping = (np.sin(2 * np.pi * pitch * tp) + 0.35 * np.sin(2 * np.pi * pitch * 2.0 * tp)) * np.exp(-tp / (0.045 - 0.02 * k))
        place(out, ping * (0.32 + 0.3 * k), at)
        at += gap
        gap = max(0.09, gap * 0.92)
    # the drone: two detuned low saws opening up, and wind rising under it
    saw = signal.sawtooth(2 * np.pi * 55.0 * total) + signal.sawtooth(2 * np.pi * 55.4 * total)
    drone = sweep_lowpass(saw, 160, 2400, curve=2.2) * (0.05 + 0.4 * (total / seconds) ** 2.5)
    wind = sweep_lowpass(noise(seconds), 300, 5000, curve=2.0) * (0.02 + 0.3 * (total / seconds) ** 3)
    out += drone + wind
    finish('halley_countdown', out, peak=0.88, fade_out=0.02)


# ---- Sighted: an airy, glassy swell from far off. -------------------------------------------------------------------
def sight():
    total = t_axis(3.6)
    out = np.zeros(len(total))
    n = noise(3.6)
    for f, g in ((440, 0.5), (660, 0.35), (880, 0.3), (1320, 0.2), (1760, 0.12)):
        out += bandpass(n, f * 0.985, f * 1.015, order=2) * g
    out *= env(total, 1.2, 1.6, 0.8)
    for _ in range(18):
        tg = t_axis(0.9)
        f = rng.uniform(2500, 6000)
        glint = np.sin(2 * np.pi * f * tg) * np.exp(-tg / 0.12) * np.clip(tg / 0.01, 0, 1)
        place(out, glint * rng.uniform(0.02, 0.06), rng.uniform(0.3, 2.6))
    finish('halley_sight', reverb(out, 2.0, 0.35)[:int(SR * 3.6)], peak=0.8, fade_out=0.4)


# ---- The approach: a roar building for 8.5 seconds, a whistle rising through it. ------------------------------------
def approach():
    seconds = 8.5
    total = t_axis(seconds)
    k = total / seconds
    roar = sweep_lowpass(brown(seconds) * 0.7 + noise(seconds) * 0.3, 90, 3800, curve=2.4)
    roar *= 0.04 + 0.96 * k ** 3.2
    rumble = lowpass(noise(seconds), 60) * 6.0 * (0.1 + 0.9 * k ** 2)
    whistle = chirp(total, 180, 1100) * 0.18 * k ** 4
    tear = highpass(noise(seconds), 3000) * 0.25 * k ** 6
    finish('halley_approach', roar + rumble + whistle + tear, peak=0.9, fade_out=0.08, drive=1.8)


# ---- The sonic boom: the double crack of the shock wave, then rolling thunder. --------------------------------------
def boom():
    total = t_axis(3.2)
    out = np.zeros(len(total))
    for at, g in ((0.0, 1.0), (0.14, 0.85)):
        tc = t_axis(0.06)
        crack = noise(0.06) * np.exp(-tc / 0.008)
        place(out, lowpass(crack, 2600) * g, at)
        tb = t_axis(0.35)
        place(out, np.sin(2 * np.pi * 48 * tb) * np.exp(-tb / 0.12) * 0.8 * g, at)
    roll = lowpass(brown(3.2), 400) * env(total, 0.12, 0.25, 0.8) * 0.7
    out += roll
    finish('halley_boom', reverb(out, 1.8, 0.3)[:int(SR * 3.2)], peak=0.9, fade_out=0.4, drive=2.0)


# ---- Touchdown: the ground taking the hit — a deep blow, rock and ice thrown up, a hiss of steam. -------------------
def touchdown():
    seconds = 4.0
    total = t_axis(seconds)
    out = np.zeros(len(total))
    tc = t_axis(0.05)
    place(out, noise(0.05) * np.exp(-tc / 0.006) * 0.9, 0.0)
    tb = t_axis(3.5)
    blow = chirp(tb, 85, 28) * np.exp(-tb / 0.45)
    out[:len(tb)] += np.tanh(blow * 2.5) * 0.9
    out += lowpass(brown(seconds), 250) * env(total, 0.02, 0.15, 0.9) * 0.8
    # debris: grains of crackle, thinning out
    for _ in range(140):
        at = rng.exponential(0.5)
        if at < seconds - 0.1:
            tg = t_axis(0.03)
            grain = bandpass(noise(0.03), 800, 5000) * np.exp(-tg / 0.006) * rng.uniform(0.1, 0.4) * np.exp(-at / 1.2)
            place(out, grain, at)
    out += highpass(noise(seconds), 4000) * env(total, 0.2, 0.6, 1.2) * 0.12
    finish('halley_touchdown', reverb(out, 2.0, 0.25)[:int(SR * seconds)], peak=0.76, fade_out=0.5, drive=1.9)


# ---- The plough: grinding through the land, rushing past. ----------------------------------------------------------
def plough():
    seconds = 2.6
    total = t_axis(seconds)
    shape = env(total, 0.08, 1.5, 0.45)
    grind = sweep_lowpass(brown(seconds) * 0.6 + noise(seconds) * 0.4, 1800, 380, curve=1.0) * shape
    rumble = lowpass(noise(seconds), 80) * 5.0 * shape
    crackle = np.zeros(len(total))
    for _ in range(260):
        at = min(2.5, rng.exponential(0.7))
        tg = t_axis(0.02)
        place(crackle, bandpass(noise(0.02), 1200, 7000) * np.exp(-tg / 0.004) * rng.uniform(0.1, 0.35), at)
    rush = sweep_lowpass(noise(seconds), 4000, 500) * 0.25 * shape
    finish('halley_plough', grind + rumble + crackle * shape + rush, peak=0.9, fade_out=0.3, drive=1.6)


# ---- Detonation: the crack, the enormous blast, ice shattering, the long roll of it over the land. ----------------
def impact():
    seconds = 6.0
    total = t_axis(seconds)
    out = np.zeros(len(total))
    tc = t_axis(0.08)
    place(out, noise(0.08) * np.exp(-tc / 0.01) * 1.0, 0.0)
    tb = t_axis(5.5)
    blast = chirp(tb, 70, 22) * np.exp(-tb / 0.7)
    out[:len(tb)] += np.tanh(blast * 3.0) * 1.0
    out += lowpass(brown(seconds), 320) * env(total, 0.03, 0.3, 1.6) * 1.0
    out += lowpass(noise(seconds), 90) * 4.0 * env(total, 0.02, 0.2, 1.0)
    # the comet's ice shattering: a spray of glassy partials
    for _ in range(90):
        at = abs(rng.normal(0.15, 0.25))
        f = rng.uniform(1800, 7500)
        tg = t_axis(0.9)
        place(out, np.sin(2 * np.pi * f * tg) * np.exp(-tg / rng.uniform(0.05, 0.3)) * rng.uniform(0.02, 0.08), at)
    out += highpass(noise(seconds), 3500) * env(total, 0.05, 0.4, 1.0) * 0.15
    finish('halley_impact', reverb(out, 2.6, 0.3)[:int(SR * seconds)], peak=0.8, fade_out=0.8, drive=2.0)


# ---- Frost spreading over the land: ice crackling outward, a cold wind. ------------------------------------------
def frost():
    seconds = 3.2
    total = t_axis(seconds)
    out = np.zeros(len(total))
    for _ in range(420):
        at = rng.gamma(2.0, 0.45)
        if at > seconds - 0.05:
            continue
        tg = t_axis(0.015)
        lo = rng.uniform(1500, 4000)
        place(out, bandpass(noise(0.015), lo, lo * 2.5) * np.exp(-tg / 0.003) * rng.uniform(0.05, 0.25), at)
    for _ in range(40):
        at = rng.uniform(0.1, 2.6)
        tg = t_axis(0.4)
        place(out, np.sin(2 * np.pi * rng.uniform(3000, 7000) * tg) * np.exp(-tg / 0.08) * 0.03, at)
    out += highpass(noise(seconds), 2000) * env(total, 0.6, 1.4, 0.8) * 0.05
    finish('halley_frost', reverb(out, 1.4, 0.25)[:int(SR * seconds)], peak=0.85, fade_out=0.4, drive=1.0)


# ---- The comet heart's hum: a low crystalline drone, beating slowly, glassy overtones. -----------------------------
def hum():
    seconds = 6.0
    total = t_axis(seconds)
    drone = (np.sin(2 * np.pi * 110.0 * total) + np.sin(2 * np.pi * 110.7 * total) * 0.8
             + np.sin(2 * np.pi * 220.3 * total) * 0.35 + np.sin(2 * np.pi * 330.0 * total) * 0.15)
    glass = (np.sin(2 * np.pi * 880.0 * total) * 0.2 + np.sin(2 * np.pi * 1318.5 * total) * 0.12) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * total))
    air = bandpass(noise(seconds), 2000, 6000) * 0.03
    shape = np.clip(total / 0.7, 0, 1) * np.clip((seconds - total) / 0.7, 0, 1)
    finish('halley_hum', (drone * 0.5 + glass + air) * shape, peak=0.62, fade_out=0.02)


# ---- Evac: a rising rush and a sparkle as the caster is lifted clear. ----------------------------------------------
def evac():
    seconds = 1.0
    total = t_axis(seconds)
    rise = sweep_lowpass(noise(seconds), 400, 7000, curve=1.0) * env(total, 0.35, 0.55, 0.15)
    tone = chirp(total, 300, 1500) * env(total, 0.3, 0.5, 0.15) * 0.4
    out = rise + tone
    for _ in range(10):
        tg = t_axis(0.3)
        place(out, np.sin(2 * np.pi * rng.uniform(3000, 6000) * tg) * np.exp(-tg / 0.06) * 0.12, rng.uniform(0.35, 0.7))
    finish('halley_evac', out, peak=0.87, fade_out=0.1)


if __name__ == '__main__':
    for make in (mark, countdown, sight, approach, boom, touchdown, plough, impact, frost, hum, evac):
        make()
