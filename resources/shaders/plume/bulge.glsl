#version 410 core

uniform float nozzle;

float bulge(float limit, float x)
{
  float decay = pow(0.5, x);
  return limit + (nozzle - limit) * decay;
}
