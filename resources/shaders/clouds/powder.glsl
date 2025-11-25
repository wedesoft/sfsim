#version 450 core

uniform float powder_decay;

float powder(float d)
{
  return 1.0 - exp(-powder_decay * d);
}
