Simple, fast counter-based random number generators based on
experimentally-derived function approximations of random permutations
and "generator stacking" of generators with co-prime periods.

Both generators pass the full PractRand test. lwrand32 (narrowly) passes BigCrush. lwrand64 consistently fails gap tests on high/low and reversed 32 bits for BigCrush. I don't realistically expect this to be a problem for most users, though.
