#version 450 core

uniform float z_far;

in VS_OUT
{
  vec3 direction;
} fs_in;

layout (location = 0) out vec4 camera_point;
layout (location = 1) out float dist;

void main ()
{
  camera_point = vec4(normalize(fs_in.direction), 0.0);
  dist = z_far;
}
