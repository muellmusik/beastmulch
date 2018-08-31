(
~path = Dialog.openPanel({|path| ~path = path.postln; }); // select exported speaker configuration

var outPath;

a = Object.readArchive(~path);

b = "Speaker, Function, BMulch Order, Hardware OP #\n";

a.do({|spk, i|
	b = b ++ spk.spec.name ++ ", "
	++ spk.name ++ ", "
	++ (i + 1) ++ ", "
	++ (spk.index + 1) ++ "\n";
});

outPath = ~path.splitext[0] ++ ".csv"
f = File(outPath, "w");
f.write(b);
f.close;
)
