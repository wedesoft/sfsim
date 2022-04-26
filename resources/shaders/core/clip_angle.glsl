#version 410 core

float M_PI = 3.14159265358;

// Clip angles between -2 pi and +2 pi to be between -pi and +pi.
float clip_angle(float angle)
{
  if (angle < -M_PI)
    return angle + 2 * M_PI;
  else if (angle >= M_PI)
    return angle - 2 * M_PI;
  else
    return angle;
}
