a = Object.readArchive("/Users/scottw/Dropbox/beast gigs/Reference docs/Dome Room info (do not delete)/Double Diamond/Dome Room Double Diamond")

b = "Name, Azimuth, Elevation, Radius, x, y, z\n";

a.do({|spk|
	b = b ++ spk.name ++ ", "
	++ spk.azi ++ ", "
	++ spk.ele ++ ", "
	++ spk.rad ++ ", "
	++ spk.x ++ ", "
	++ spk.y ++ ", "
	++ spk.z ++ "\n";
});

b = b ++ "\nNB Cartesian coords origin is head height room centre\n";

f = File("/tmp/DomeDD.csv", "w");
f.write(b);
f.close;