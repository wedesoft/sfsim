#version 450 core

uniform float plume_nozzle;
uniform float plume_min_limit;
uniform float plume_max_slope;

float plume_limit(float pressure);
float plume_phase(float x, float limit);

float plume_bulge(float pressure, float x)
{
  float limit = plume_limit(pressure);
  float range = plume_nozzle - limit;
  if (plume_nozzle < limit) {
    float equilibrium = plume_min_limit * plume_min_limit / (plume_nozzle * plume_nozzle);
    float base = exp(-plume_max_slope * (equilibrium - pressure) / (equilibrium * limit));
    float decay = pow(base, x);
    return limit + range * decay;
  } else {
    float phase = plume_phase(x, limit);
    float bumps = range * abs(sin(phase));
    return limit + bumps;
  };
}
