all10: all10g.tgz
	tar -xvf $<
	touch $@

elevation.A1.raw: all10
	cat all10/a10g all10/e10g > $@

elevation.B1.raw: all10
	cat all10/b10g all10/f10g > $@

elevation.C1.raw: all10
	cat all10/c10g all10/g10g > $@

elevation.D1.raw: all10
	cat all10/d10g all10/h10g > $@

elevation.A2.raw: all10
	cat all10/i10g all10/m10g > $@

elevation.B2.raw: all10
	cat all10/j10g all10/n10g > $@

elevation.C2.raw: all10
	cat all10/k10g all10/o10g > $@

elevation.D2.raw: all10
	cat all10/l10g all10/p10g > $@
