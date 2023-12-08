#version 410 core

in VS_OUT
{
  vec3 direction;
} fs_in;

out vec4 fragColor;

vec4 cloud_atmosphere(vec3 fs_in_direction);

void main()
{
  fragColor = cloud_atmosphere(fs_in.direction);
}
