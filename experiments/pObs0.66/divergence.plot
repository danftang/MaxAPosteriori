se boxwidth 0.2
set style fill solid
plot [][0:7] "divergences.dat" using 1:2 with boxes title "Average distance of MAP"
replot "divergences.dat" using ($1+0.2):3 with boxes title "Average distance of online"
replot "divergences.dat" using ($1+0.4):4 with boxes title "Average distance of random point"
