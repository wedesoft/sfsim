#version 450 core

in VS_OUT
{
  vec4 camera_point;
} fs_in;

layout (location = 0) out vec4 camera_point;
layout (location = 1) out float distance;

void main()
{
  camera_point = fs_in.camera_point;
  distance = length(fs_in.camera_point.xyz);
}
