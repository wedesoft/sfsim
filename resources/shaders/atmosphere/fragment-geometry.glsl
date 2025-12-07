#version 450 core

in VS_OUT
{
  vec3 direction;
} fs_in;

layout (location = 0) out vec4 camera_point;
layout (location = 1) out float distance;

void main ()
{
  camera_point = vec4(normalize(fs_in.direction), 0.0);
  distance = 0.0;
}
