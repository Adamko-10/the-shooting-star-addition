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
    # written in blocks: libsndfile's Vorbis encoder can crash the whole process when handed a long sound at once
    with sf.SoundFile(os.path.join(OUT, name + '.ogg'), 'w', SR, 1, format='OGG', subtype='VORBIS') as f:
        data = x.astype(np.float32)
        for i in range(0, len(data), 8192):
            f.write(data[i:i + 8192])
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


# ---- Dusk: the sky goes dark as the uplink locks on. A long fall in the deep, the air drawing in, a cold choir. -------
def dusk():
    seconds = 5.5
    total = t_axis(seconds)
    k = total / seconds
    # the floor drops out: a sub tone sliding down an octave
    fall = np.sin(2 * np.pi * np.cumsum(70.0 * 2.0 ** (-k * 1.2)) / SR) * env(total, 0.6, 2.8, 1.2) * 0.8
    # the air drawing in: noise swelling and closing, like a breath held
    air = sweep_lowpass(noise(seconds), 4500, 260, curve=0.8) * env(total, 1.8, 2.2, 1.1) * 0.5
    # a cold choir fading in as the stars come out: detuned voices, slowly beating
    voices = np.zeros(len(total))
    for f, g in ((220.0, 0.30), (220.9, 0.25), (329.6, 0.22), (330.4, 0.18), (440.0, 0.14), (493.9, 0.10), (659.3, 0.07)):
        voices += np.sin(2 * np.pi * f * total + 3.0 * np.sin(2 * np.pi * 0.21 * total)) * g
    voices = lowpass(voices, 2400) * np.clip((total - 1.0) / 2.5, 0, 1) * np.clip((seconds - total) / 1.2, 0, 1) * 0.55
    out = fall + air + voices
    for _ in range(14):
        tg = t_axis(1.2)
        place(out, np.sin(2 * np.pi * rng.uniform(2200, 5200) * tg) * np.exp(-tg / 0.25) * np.clip(tg / 0.02, 0, 1) * rng.uniform(0.02, 0.05),
              rng.uniform(2.0, 4.6))
    finish('halley_dusk', reverb(out, 2.4, 0.35)[:int(SR * seconds)], peak=0.75, fade_out=0.6)


# ---- A fragment bursts high up: a sharp crack, a spray of crackles, thunder rolling away. -------------------------------
def burst():
    seconds = 3.0
    total = t_axis(seconds)
    out = np.zeros(len(total))
    tc = t_axis(0.05)
    place(out, lowpass(noise(0.05) * np.exp(-tc / 0.007), 4000) * 1.0, 0.0)
    tb = t_axis(0.5)
    place(out, np.sin(2 * np.pi * 55 * tb) * np.exp(-tb / 0.15) * 0.7, 0.0)
    for _ in range(40):
        tk = t_axis(0.03)
        place(out, highpass(noise(0.03), 1500) * np.exp(-tk / 0.004) * rng.uniform(0.05, 0.25), 0.08 + rng.exponential(0.35))
    roll = lowpass(brown(seconds), 140) * env(total, 0.05, 0.25, 0.9) * 0.6
    finish('halley_burst', reverb(out + roll, 1.8, 0.3)[:int(SR * seconds)], peak=0.85, fade_out=0.3, drive=1.4)


# =====================================================================================================================
# SS-06 Luna's sounds. Appended after SS-05's so the shared `rng` has already produced every draw halley_* consumes -
# re-running this file leaves every halley_*.ogg byte-identical, as long as these run last (see __main__ below).
# =====================================================================================================================

# ---- The alarm under EARTH SYSTEM SHUT DOWN: not a klaxon - a cool, ominous "system shutdown". Quiet on purpose; ----
# everything else in the skill is loud, so this is the hush before it. A deep power-down sweep collapsing toward
# sub-bass, a sub hit as the power finally drops, a restrained low two-tone warning (a few slow beats, not a siren),
# and a sparse digital glitch tail. Mastered well under the others (peak -9 dBFS).
ALARM_PEAK = 10.0 ** (-9.0 / 20.0)


def luna_alarm():
    seconds = 4.0
    total = t_axis(seconds)
    out = np.zeros(len(total))
    # the power-down sweep: a synthetic tone collapsing from mid register into the sub, like a system losing power
    sweep_seconds = 1.6
    ts = t_axis(sweep_seconds)
    sweep_env = env(ts, 0.1, 1.15, 0.32)
    place(out, lowpass(chirp(ts, 360.0, 38.0), 900) * sweep_env * 0.55, 0.0)
    place(out, lowpass(chirp(ts, 354.0, 37.2), 900) * sweep_env * 0.35, 0.02)  # a touch detuned, for weight
    # the sub-bass hit as the power finally drops out
    th = t_axis(0.9)
    place(out, np.sin(2 * np.pi * 42.0 * th) * np.exp(-th / 0.3) * 0.85, 1.45)
    tc = t_axis(0.03)
    place(out, lowpass(noise(0.03), 800) * np.exp(-tc / 0.006) * 0.3, 1.45)
    # a restrained low two-tone warning: a few slow, soft beats - never a continuous siren
    for at, hz in ((1.95, 165.0), (2.4, 131.0), (3.0, 165.0), (3.45, 131.0)):
        tp = t_axis(0.3)
        tone = np.sin(2 * np.pi * hz * tp) * env(tp, 0.03, 0.17, 0.07) * 0.28
        place(out, tone, at)
    # a sparse digital glitch tail
    for _ in range(9):
        at = rng.uniform(3.1, seconds - 0.04)
        tg = t_axis(0.015)
        place(out, bandpass(noise(0.015), 1800, 6000) * np.exp(-tg / 0.003) * rng.uniform(0.06, 0.16), at)
    finish('luna_alarm', reverb(out, 1.6, 0.2)[:int(SR * seconds)], peak=ALARM_PEAK, fade_out=0.3, drive=1.2)


# ---- The moon cracks open, far up in the sky: a huge, deep crack, heavy and distant, then creaking as the seams spread.
def luna_crack():
    seconds = 3.0
    total = t_axis(seconds)
    out = np.zeros(len(total))
    # a soft-edged, heavy crack (distant: no sharp transient, no high end)
    tc = t_axis(0.16)
    place(out, lowpass(noise(0.16) * np.exp(-tc / 0.022), 1300) * 0.85, 0.05)
    tb = t_axis(1.0)
    place(out, np.sin(2 * np.pi * 36 * tb) * np.exp(-tb / 0.38) * 0.9, 0.05)
    place(out, np.sin(2 * np.pi * 22 * tb) * np.exp(-tb / 0.55) * 0.6, 0.12)
    # groaning: slow, huge, grinding chirps sliding down, like stressed stone far overhead
    for i in range(4):
        at = 0.35 + i * 0.62 + rng.uniform(-0.05, 0.05)
        tk = t_axis(0.55)
        creak = chirp(tk, rng.uniform(220, 420), rng.uniform(55, 120)) * np.exp(-tk / 0.4) * rng.uniform(0.2, 0.35)
        place(out, lowpass(creak, 850), at)
    rumble = lowpass(brown(seconds), 110) * env(total, 0.06, 0.6, 1.0) * 0.6
    finish('luna_crack', reverb(out + rumble, 2.6, 0.45)[:int(SR * seconds)], peak=0.82, fade_out=0.5, drive=1.3)


# ---- The fall: 18.0 s, FALL to CONTACT - epic and slow. A deep rumble growing, a rising choir-like pad for awe, -------
# wind and pressure building, ending in a near-overwhelming roar that cuts hard right at the impact (no tail: the
# touchdown sound takes over there).
def luna_fall():
    seconds = 18.0
    total = t_axis(seconds)
    k = total / seconds
    # the deep rumble, growing the whole way
    rumble = lowpass(noise(seconds), 50) * 7.5 * (0.04 + 0.96 * k ** 2.2)
    # the main roar: a slow climb from a low growl into a wall of sound
    roar = sweep_lowpass(brown(seconds) * 0.65 + noise(seconds) * 0.35, 55, 4600, curve=2.9)
    roar *= 0.02 + 0.98 * k ** 3.6
    # a rising tonal drone / choir-like pad, swelling in for awe
    voices = np.zeros(len(total))
    for f, g in ((55.0, 0.55), (55.4, 0.42), (82.5, 0.32), (110.3, 0.22), (164.8, 0.14), (220.6, 0.08)):
        voices += np.sin(2 * np.pi * f * total + 2.2 * np.sin(2 * np.pi * 0.12 * total)) * g
    voices = lowpass(voices, 1300) * (0.04 + 0.9 * k ** 1.7)
    # wind and pressure increasing
    wind = sweep_lowpass(noise(seconds), 240, 3600, curve=1.9) * (0.03 + 0.55 * k ** 2.6)
    whistle = chirp(total, 110, 1500) * 0.15 * k ** 5
    tear = highpass(noise(seconds), 3000) * 0.2 * k ** 7
    # fragments breaking off and streaming behind, scattered crackles thinning as it closes in
    crackle = np.zeros(len(total))
    for _ in range(130):
        at = min(seconds - 0.08, abs(rng.exponential(seconds * 0.4)))
        tg = t_axis(0.03)
        place(crackle, bandpass(noise(0.03), 900, 6000) * np.exp(-tg / 0.006) * rng.uniform(0.05, 0.18), at)
    out = roar + rumble + voices + wind + whistle + tear + crackle
    finish('luna_fall', reverb(out, 2.0, 0.22)[:int(SR * seconds)], peak=0.82, fade_out=0.035, drive=2.1)


# ---- Entry: 5.0 s, ENTRY to CONTACT - hitting the atmosphere: tearing plasma, crackling fire, a rising whistle/scream.
def luna_entry():
    seconds = 5.0
    total = t_axis(seconds)
    k = total / seconds
    roar = sweep_lowpass(brown(seconds) * 0.5 + noise(seconds) * 0.5, 450, 5400, curve=1.7) * (0.28 + 0.72 * k ** 1.6)
    fire = bandpass(noise(seconds), 1800, 7200) * (0.2 + 0.55 * np.sin(2 * np.pi * 8.0 * total) ** 2) * (0.22 + 0.65 * k)
    tear = highpass(noise(seconds), 3200) * 0.4 * (0.15 + 0.85 * k ** 2.2)
    scream = chirp(total, 380, 2800) * 0.32 * k ** 2.5  # the rising whistle/scream of the bow shock
    shock = chirp(total, 2600, 350) * np.exp(-total / 1.6) * 0.28
    out = roar + fire + tear + scream + shock
    finish('luna_entry', reverb(out, 1.4, 0.2)[:int(SR * seconds)], peak=0.78, fade_out=0.08, drive=2.2)


# ---- Impact: the moon touches down - a colossal, punchy, cinematic hit: a sharp transient, a sub-bass drop, a long --
# rolling thunder-like tail (with a couple of fainter re-thunders rolling on), and debris landing.
def luna_impact():
    seconds = 9.0
    total = t_axis(seconds)
    out = np.zeros(len(total))
    # the sharp transient
    tc = t_axis(0.09)
    place(out, noise(0.09) * np.exp(-tc / 0.009) * 1.0, 0.0)
    # the sub-bass drop
    tb = t_axis(6.5)
    blast = chirp(tb, 50, 14) * np.exp(-tb / 1.1)
    out[:len(tb)] += np.tanh(blast * 3.3) * 1.0
    # the long rolling thunder-like tail
    out += lowpass(brown(seconds), 220) * env(total, 0.05, 0.6, 2.8) * 1.0
    out += lowpass(noise(seconds), 60) * 4.5 * env(total, 0.04, 0.4, 1.8)
    # a couple of fainter re-thunders, rolling on after the main hit
    for at, g, dec in ((1.7, 0.5, 1.2), (3.6, 0.35, 1.4), (5.9, 0.22, 1.6)):
        tt = t_axis(dec * 2)
        place(out, lowpass(brown(dec * 2), 180) * np.exp(-tt / dec) * g, at)
    # the ground-shake thump
    tt = t_axis(2.4)
    place(out, np.sin(2 * np.pi * 22 * tt) * np.exp(-tt / 0.7) * 0.85, 0.1)
    # debris thrown and landing
    for _ in range(70):
        at = 0.3 + rng.exponential(1.8)
        if at < seconds - 0.15:
            tg = t_axis(0.05)
            place(out, bandpass(noise(0.05), 500, 4500) * np.exp(-tg / 0.012) * rng.uniform(0.08, 0.3), at)
    out += highpass(noise(seconds), 3000) * env(total, 0.08, 0.6, 1.4) * 0.1
    finish('luna_impact', reverb(out, 4.2, 0.35)[:int(SR * seconds)], peak=0.82, fade_out=1.2, drive=2.3)


# ---- Settle: it ploughs on down and comes to rest - heavy grinding and a deep collapse, kept low throughout. ---------
def luna_settle():
    seconds = 3.0
    total = t_axis(seconds)
    shape = env(total, 0.12, 1.7, 0.55)
    grind = sweep_lowpass(brown(seconds) * 0.65 + noise(seconds) * 0.35, 900, 220, curve=1.0) * shape
    rumble = lowpass(noise(seconds), 55) * 6.0 * shape
    tt = t_axis(1.0)
    thud = np.zeros(len(total))
    place(thud, np.sin(2 * np.pi * 30 * tt) * np.exp(-tt / 0.35) * 0.7, 0.05)
    collapse = np.zeros(len(total))
    for _ in range(160):
        at = min(seconds - 0.05, rng.exponential(0.95))
        tg = t_axis(0.03)
        place(collapse, bandpass(noise(0.03), 300, 2200) * np.exp(-tg / 0.008) * rng.uniform(0.08, 0.28), at)
    out = grind + rumble + thud + collapse * shape
    finish('luna_settle', reverb(out, 1.8, 0.25)[:int(SR * seconds)], peak=0.84, fade_out=0.4, drive=1.4)


# ---- Debris: the aftermath - a low wind/ash drone with distant rumbles and a few distant rock falls, fading smoothly
# to silence. Kept entirely low: no high-pitched crackle, no odd tones, nothing that doesn't belong at the end.
def luna_debris():
    seconds = 8.0
    total = t_axis(seconds)
    out = np.zeros(len(total))
    # the low wind/ash drone underneath the whole scene
    drone = lowpass(noise(seconds), 240) * (1.0 + 0.2 * np.sin(2 * np.pi * 0.09 * total + 0.6))
    out += drone * env(total, 1.0, 5.0, 1.4) * 0.22
    # distant rumbles rolling through, far off and soft
    for _ in range(5):
        at = rng.uniform(0.4, 5.5)
        dur = rng.uniform(1.3, 2.4)
        tt = t_axis(dur)
        rumble = lowpass(brown(dur), 140) * np.clip(tt / 0.2, 0, 1) * np.exp(-tt / (dur * 0.45)) * rng.uniform(0.16, 0.3)
        place(out, rumble, at)
    # a few distant rock falls: low, soft, dusty - never bright
    for _ in range(6):
        at = rng.uniform(0.6, 5.8)
        weight = 1.0 - at / seconds
        tb = t_axis(0.55)
        tumble = lowpass(noise(0.55), 350) * np.exp(-tb / 0.22) * rng.uniform(0.09, 0.22) * (0.3 + 0.8 * weight)
        place(out, tumble, at)
        tt = t_axis(0.35)
        place(out, np.sin(2 * np.pi * rng.uniform(42, 70) * tt) * np.exp(-tt / 0.14) * 0.16 * (0.3 + 0.8 * weight), at)
    # fade smoothly to silence over the last 2.5 s
    fade_start = seconds - 2.5
    kf = np.clip((total - fade_start) / (seconds - fade_start), 0.0, 1.0)
    out *= 0.5 * (1.0 + np.cos(np.pi * kf))
    finish('luna_debris', reverb(out, 2.2, 0.3)[:int(SR * seconds)], peak=0.62, fade_out=0.3, drive=0.0)


if __name__ == '__main__':
    for make in (mark, countdown, sight, approach, boom, touchdown, plough, impact, frost, hum, evac, dusk, burst,
                 luna_alarm, luna_crack, luna_fall, luna_entry, luna_impact, luna_settle, luna_debris):
        make()
