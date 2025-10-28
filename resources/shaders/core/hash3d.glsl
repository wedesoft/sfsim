#version 410 core

float hash3d(vec3 point)
{
  float ramp = point.x + point.y * 37.0 + point.z * 521.0;
  return fract(sin(ramp * 1.333) * 100003.9);
}

