#version 450 core

uniform float nozzle;
uniform float min_limit;
uniform float max_slope;

float plume_limit(float pressure);
float plume_phase(float x, float limit);

float bulge(float pressure, float x)
{
  float limit = plume_limit(pressure);
  float range = nozzle - limit;
  if (nozzle < limit) {
    float equilibrium = min_limit * min_limit / (nozzle * nozzle);
    float base = exp(-max_slope * (equilibrium - pressure) / (equilibrium * limit));
    float decay = pow(base, x);
    return limit + range * decay;
  } else {
    float phase = plume_phase(x, limit);
    float bumps = range * abs(sin(phase));
    return limit + bumps;
  };
}
