# Finding the Max-A-Posteriori behaviour of agents in an agent based model

This is an implementation of an algorithm to find the maximum-a-posteriori set of events given a set of partial, noisy observations of some dynamical system. In particular, we consider the case when the dynamical system is a (possibly stochastic) time-stepping agent-based model with a discrete state space, the  (possibly noisy) observations are the number of agents that have some given property and the events we're interested in are the expressed behaviours of the agents.

The problem can be reduced to an integer linear programming problem which can subsequently be solved numerically using a standard branch-and-cut algorithm as implemented in Google's OR-Tools library. There are two algorithms, an offline algorithm that finds the maximum-a-posteriori expressed behaviours given a set of observations over a finite time window, and an online algorithm that incrementally builds a feasible set of behaviours from a stream of observations that may have no natural beginning or end.

Example uses of both algorithms are given for a spatial predator-prey model on a 32x32 grid with an initial population of 100 agents.

Please see [this paper](doc/MAPOrbit.pdf) for more details of the algorithm.
