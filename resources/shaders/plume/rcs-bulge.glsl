#version 450 core

uniform float rcs_nozzle;
uniform float rcs_min_limit;
uniform float rcs_max_slope;

float rcs_limit(float pressure);

float rcs_bulge(float pressure, float x)
{
  float limit = rcs_limit(pressure);
  float range = rcs_nozzle - limit;
  float equilibrium = rcs_min_limit * rcs_min_limit / (rcs_nozzle * rcs_nozzle);
  float base = exp(-rcs_max_slope * (equilibrium - pressure) / (equilibrium * limit));
  float decay = pow(base, x);
  return limit + range * decay;
}

