#version 410 core

vec3 face1_vector(vec2 texcoord)
{
  return vec3(1, 1 - 2 * texcoord.t, 1 - 2 * texcoord.s);
}

vec3 face2_vector(vec2 texcoord)
{
  return vec3(-1, 1 - 2 * texcoord.t, 2 * texcoord.s - 1);
}

vec3 face3_vector(vec2 texcoord)
{
  return vec3(2 * texcoord.s - 1, 1, 2 * texcoord.t - 1);
}

vec3 face4_vector(vec2 texcoord)
{
  return vec3(2 * texcoord.s - 1, -1, 1 - 2 * texcoord.t);
}

vec3 face5_vector(vec2 texcoord)
{
  return vec3(2 * texcoord.s - 1, 1 - 2 * texcoord.t, 1);
}

vec3 face6_vector(vec2 texcoord)
{
  return vec3(1 - 2 * texcoord.s, 1 - 2 * texcoord.t, -1);
}
