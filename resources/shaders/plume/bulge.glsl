#version 410 core

uniform float nozzle;
uniform float min_limit;
uniform float max_slope;
uniform float omega_factor;

float bulge(float pressure, float x)
{
  float limit = min_limit / sqrt(pressure);
  float range = nozzle - limit;
  if (nozzle < limit) {
    float equilibrium = min_limit * min_limit / (nozzle * nozzle);
    float base = exp(-max_slope * (equilibrium - pressure) / (equilibrium * limit));
    float decay = pow(base, x);
    return limit + range * decay;
  } else {
    float bumps = range * abs(sin(x * omega_factor * range));
    return limit + bumps;
  };
}
