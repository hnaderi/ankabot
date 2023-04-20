{
  inputs = {
    typelevel-nix.url = "github:typelevel/typelevel-nix";
    nixpkgs.follows = "typelevel-nix/nixpkgs";
    flake-utils.follows = "typelevel-nix/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, typelevel-nix }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        overlay = final: prev: { jdk = prev.jdk17; };
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ typelevel-nix.overlay overlay ];
        };

      in {
        devShell = pkgs.devshell.mkShell {
          imports = [ typelevel-nix.typelevelShell ];
          name = "knowledge-base-shell";
        };
      });
}
