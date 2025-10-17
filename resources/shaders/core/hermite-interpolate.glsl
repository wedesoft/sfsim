#version 410 core


float hermite_interpolate(float a, float b, float t)
{
    return mix(a, b, t * t * (3.0 - 2.0 * t));
}
