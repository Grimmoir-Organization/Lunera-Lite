#!/usr/bin/env python3
"""Generate the local scanner fixture; no external audio or datasets required."""
import math,struct,sys,wave,pathlib
target=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'app/build/test-long.wav')
target.parent.mkdir(parents=True,exist_ok=True)
one_second=b''.join(struct.pack('<h',int(math.sin(i*2*math.pi*440/8000)*2000))for i in range(8000))
with wave.open(str(target),'wb') as output:
    output.setnchannels(1);output.setsampwidth(2);output.setframerate(8000)
    for _ in range(90):output.writeframes(one_second)
print(target)
