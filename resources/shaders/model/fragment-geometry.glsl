#version 450 core

in VS_OUT
{
  vec4 camera_point;
} fs_in;

layout (location = 0) out vec4 camera_point;
layout (location = 1) out float dist;

void main()
{
  camera_point = vec4(normalize(fs_in.camera_point.xyz), 0.0);
  dist = length(fs_in.camera_point.xyz);
}
