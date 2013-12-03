

x = TabFileReader.read("/private/tmp/BEAST Vanishing Point hardware (JC).txt").postcs;

l = x.copyToEnd(1).collectAs({|speak| BMSpeaker(speak[1].asSymbol.postln, speak[3].interpret - 1) }, BMInOutArray);

l.keys

l.writeArchive("/tmp/speakerList");


"/private/tmp/1302 Bramall hardware.txt"