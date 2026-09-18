import math, wave, os, struct
root = '/home/ubuntu/work/sam-launcher/app/src/main/res/raw'
os.makedirs(root, exist_ok=True)
rate = 22050
def write(name, duration, fn):
    n = int(rate * duration)
    with wave.open(os.path.join(root, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        frames = bytearray()
        for i in range(n):
            t = i / rate
            sample = max(-1, min(1, fn(t, duration)))
            frames += struct.pack('<h', int(sample * 28000))
        w.writeframes(frames)
write('sfx_click', .055, lambda t,d: math.sin(2*math.pi*(900-350*t/d)*t)*math.exp(-55*t))
write('sfx_panel', .22, lambda t,d: (math.sin(2*math.pi*(180+620*t/d)*t) + .25*math.sin(2*math.pi*70*t))*math.exp(-14*t))
write('sfx_beep', .12, lambda t,d: math.sin(2*math.pi*1150*t)*math.exp(-24*t))
