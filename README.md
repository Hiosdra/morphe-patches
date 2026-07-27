# Hiosdra Patches

Personal, community-maintained patches compatible with Morphe.

## About

This repository is an independent project and is not authored by or affiliated
with the Morphe project. It publishes source code and Morphe patch bundles, not
modified APK files.

Use these patches only with applications you own or are authorized to modify.

## Add to Morphe

[Add Hiosdra Patches to Morphe](https://morphe.software/add-source?github=Hiosdra%2Fmorphe-patches)

You can also add the following GitHub URL manually in Morphe's patch source
manager:

```text
https://github.com/Hiosdra/morphe-patches
```

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->

<!-- Do not modify this section by hand. The patch list is generated when release.yml creates a new release.

     If you wish for the patches list to be collapsed, then remove the word 'EXPANDED' from the comment tag above.

     If you wish to manually keep this list updated then remove the PATCHES_START and PATCHES_END
     comment blocks entirely. -->

The patch list is generated automatically during the first release.

&nbsp;

## 🚀 Get started

To set up development:

1. Follow the [Morphe development setup](https://github.com/MorpheApp/morphe-documentation/blob/main/docs/morphe-development/README.md), including the GitHub package credentials described in the [patcher setup guide](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md#-prepare-the-environment).
2. In `Hiosdra/morphe-patches`, enable **Allow GitHub Actions to create and approve pull requests** in Settings > Actions > General > Workflow permissions.
3. Develop changes on `dev` and use semantic commit messages.

🎉 You are now ready to start creating patches!

## Usage

To develop and release your Patches using this template:

- For a local build, run `./gradlew buildAndroid`. The `.mpp` file is written to `patches/build/libs/patches-*.mpp`.
- Apply patches only to applications you own or are authorized to test.
- Use [semantic commit](https://kapeli.com/cheat_sheets/Semantic_Commits.docset/Contents/Resources/Documents/index) messages. `feat:`, `fix:`, and `chore:` are the supported everyday types.
- Commits of `fix:` and `feat:` automatically generate pre-releases; `chore:` does not create a release.
- When `dev` is ready, merge it into `main` without squashing and let `release.yml` create the stable release.

## Development notes
- See the [patcher documentation](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/1_patcher_intro.md)
  for patch types, fingerprints, and execution contexts.
- Do not manually edit any generated files such as: `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`.
  These files will be automatically updated in the release action.
- Do not force push any semantic release commits or you will break the release. To 'redo' the last release then:
  - Git drop the last dev/main semantic release commit you want to redo.
  - Delete the release from the release area of this repo and delete the tag
  - Make any other changes you wish to do
  - Force push dev/main branch
  - A new replacement release will be created by `release.yml`


<!-- The patches end tag is intentionally placed here so the first release will cleanup
     this readme of all developer instructions above. -->
<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=Hiosdra%2Fmorphe-patches

Or manually add this repository URL as a patch source in Morphe: https://github.com/Hiosdra/morphe-patches

### 🛠️ Building

To build Hiosdra Patches, follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## 📜 License

Hiosdra Patches is licensed under the [GNU General Public License v3.0](LICENSE).
