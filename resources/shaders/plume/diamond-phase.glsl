#version 450 core

#define M_PI 3.1415926535897932384626433832795

float plume_phase(float x, float limit);

float diamond_phase(float x, float limit)
{
  float phase = plume_phase(x, limit);
  return mod(phase - 0.3 * M_PI, M_PI) - 0.7 * M_PI;
}
