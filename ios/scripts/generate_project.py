#!/usr/bin/env python3
"""Deterministic, dependency-free Xcode project generator. Never touches Android."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
objects={}
def uid(name):return hashlib.sha1(name.encode()).hexdigest()[:24].upper()
def add(name, value):
 key=uid(name);objects[key]=value;return key
def quote(value):return json.dumps(str(value))
def obj(isa,**kwargs):return {'isa':isa,**kwargs}
def ref(path,kind):return add('ref:'+path,obj('PBXFileReference',lastKnownFileType=kind,path=quote(path),sourceTree=quote('<group>')))
def build(name,file):return add('build:'+name,obj('PBXBuildFile',fileRef=file))
app_sources=sorted(str(p.relative_to(ROOT)) for p in (ROOT/'Pocket').glob('*.swift'))
unit_sources=sorted(str(p.relative_to(ROOT)) for p in (ROOT/'PocketTests').glob('*.swift'))
ui_sources=sorted(str(p.relative_to(ROOT)) for p in (ROOT/'PocketUITests').glob('*.swift'))
resources=sorted(str(p.relative_to(ROOT)) for p in (ROOT/'Pocket/Resources').iterdir() if p.is_file())
fixtures=sorted(str(p.relative_to(ROOT)) for p in (ROOT/'PocketTests/Fixtures').glob('*'))
children=[]
def phase(name,paths,isa):
 builds=[]
 for p in paths:
  f=ref(p,'sourcecode.swift' if p.endswith('.swift') else 'text.json' if p.endswith('.json') else 'image.webp' if p.endswith('.webp') else 'text')
  children.append(f);builds.append(build(name+':'+p,f))
 return add(name,obj(isa,buildActionMask='2147483647',files=builds,runOnlyForDeploymentPostprocessing='0'))
app_phases=[phase('app.sources',app_sources,'PBXSourcesBuildPhase'),phase('app.resources',resources,'PBXResourcesBuildPhase'),phase('app.frameworks',[],'PBXFrameworksBuildPhase')]
unit_phases=[phase('unit.sources',unit_sources,'PBXSourcesBuildPhase'),phase('unit.resources',fixtures,'PBXResourcesBuildPhase'),phase('unit.frameworks',[],'PBXFrameworksBuildPhase')]
ui_phases=[phase('ui.sources',ui_sources,'PBXSourcesBuildPhase'),phase('ui.frameworks',[],'PBXFrameworksBuildPhase')]
products=[]
def configuration(name,settings):return add(name,obj('XCBuildConfiguration',buildSettings=settings,name=quote(name.rsplit('.',1)[-1])))
def configlist(name,settings):
 configs=[configuration(name+'.'+mode,settings | ({'SWIFT_OPTIMIZATION_LEVEL':quote('-Onone'),'DEBUG_INFORMATION_FORMAT':quote('dwarf'),'SWIFT_ACTIVE_COMPILATION_CONDITIONS':quote('DEBUG')} if mode=='Debug' else {'SWIFT_OPTIMIZATION_LEVEL':quote('-O'),'DEBUG_INFORMATION_FORMAT':quote('dwarf-with-dsym')})) for mode in ['Debug','Release']]
 return add(name+'.configs',obj('XCConfigurationList',buildConfigurations=configs,defaultConfigurationIsVisible='0',defaultConfigurationName=quote('Release')))
project_id=uid('project');app_id=uid('Pocket')
base={'IPHONEOS_DEPLOYMENT_TARGET':quote('17.0'),'SDKROOT':quote('iphoneos'),'SWIFT_VERSION':quote('5.0'),'CLANG_ENABLE_MODULES':'YES','TARGETED_DEVICE_FAMILY':quote('1,2'),'CODE_SIGN_STYLE':quote('Automatic'),'DEVELOPMENT_TEAM':quote(''),'SWIFT_STRICT_CONCURRENCY':quote('targeted'),'ENABLE_TESTABILITY':'YES'}
def target(name,phases,product_type,settings,deps=[]):
 product=add('product:'+name,obj('PBXFileReference',explicitFileType='wrapper.application' if name=='Pocket' else 'wrapper.cfbundle',includeInIndex='0',path=quote(name+('.app' if name=='Pocket' else '.xctest')),sourceTree=quote('BUILT_PRODUCTS_DIR')));products.append(product)
 return add(name,obj('PBXNativeTarget',buildConfigurationList=configlist(name,base|settings),buildPhases=phases,buildRules=[],dependencies=deps,name=quote(name),productName=quote(name),productReference=product,productType=quote(product_type)))
app=target('Pocket',app_phases,'com.apple.product-type.application',{'PRODUCT_BUNDLE_IDENTIFIER':quote('cl.demeberant.pocket.ios'),'PRODUCT_NAME':quote('$(TARGET_NAME)'),'GENERATE_INFOPLIST_FILE':'YES','INFOPLIST_KEY_CFBundleDisplayName':quote('Pocket'),'INFOPLIST_KEY_UIApplicationSceneManifest_Generation':'YES','INFOPLIST_KEY_UILaunchScreen_Generation':'YES','INFOPLIST_KEY_UISupportedInterfaceOrientations':quote('UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight'),'MARKETING_VERSION':quote('0.1.0'),'CURRENT_PROJECT_VERSION':'1','OTHER_LDFLAGS':[quote('-lsqlite3')],'LD_RUNPATH_SEARCH_PATHS':[quote('$(inherited)'),quote('@executable_path/Frameworks')]})
proxy=add('proxy',obj('PBXContainerItemProxy',containerPortal=project_id,proxyType='1',remoteGlobalIDString=app,remoteInfo=quote('Pocket')))
dependency=add('dependency',obj('PBXTargetDependency',target=app,targetProxy=proxy))
unit=target('PocketTests',unit_phases,'com.apple.product-type.bundle.unit-test',{'PRODUCT_BUNDLE_IDENTIFIER':quote('cl.demeberant.pocket.tests'),'PRODUCT_NAME':quote('$(TARGET_NAME)'),'GENERATE_INFOPLIST_FILE':'YES','TEST_HOST':quote('$(BUILT_PRODUCTS_DIR)/Pocket.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Pocket'),'BUNDLE_LOADER':quote('$(TEST_HOST)'),'LD_RUNPATH_SEARCH_PATHS':[quote('$(inherited)'),quote('@executable_path/Frameworks'),quote('@loader_path/Frameworks')]},[dependency])
ui=target('PocketUITests',ui_phases,'com.apple.product-type.bundle.ui-testing',{'PRODUCT_BUNDLE_IDENTIFIER':quote('cl.demeberant.pocket.uitests'),'PRODUCT_NAME':quote('$(TARGET_NAME)'),'GENERATE_INFOPLIST_FILE':'YES','TEST_TARGET_NAME':quote('Pocket')},[dependency])
prodgroup=add('products',obj('PBXGroup',children=products,name=quote('Products'),sourceTree=quote('<group>')))
main=add('main',obj('PBXGroup',children=children+[prodgroup],sourceTree=quote('<group>')))
add('project',obj('PBXProject',attributes={'LastUpgradeCheck':'1640','BuildIndependentTargetsInParallel':'YES'},buildConfigurationList=configlist('project',base),compatibilityVersion=quote('Xcode 14.0'),developmentRegion=quote('es'),hasScannedForEncodings='0',knownRegions=[quote('es'),quote('en'),quote('Base')],mainGroup=main,productRefGroup=prodgroup,projectDirPath=quote(''),projectRoot=quote(''),targets=[app,unit,ui]))
def serialize(value,indent=0):
 if isinstance(value,dict):return '{\n'+''.join('\t'*(indent+1)+k+' = '+serialize(v,indent+1)+';\n' for k,v in value.items())+'\t'*indent+'}'
 if isinstance(value,list):return '('+', '.join(serialize(v,indent) for v in value)+')'
 return str(value)
project=ROOT/'Pocket.xcodeproj';project.mkdir(exist_ok=True)
(project/'project.pbxproj').write_text('// !$*UTF8*$!\n'+serialize({'archiveVersion':'1','classes':{},'objectVersion':'56','objects':objects,'rootObject':project_id})+'\n')
scheme=project/'xcshareddata/xcschemes';scheme.mkdir(parents=True,exist_ok=True)
def buildable(name):return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{uid(name)}" BuildableName="{name}{".app" if name=="Pocket" else ".xctest"}" BlueprintName="{name}" ReferencedContainer="container:Pocket.xcodeproj"/>'
(scheme/'Pocket.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1640" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{buildable('Pocket')}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{buildable('PocketTests')}</TestableReference><TestableReference skipped="NO">{buildable('PocketUITests')}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable('Pocket')}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable('Pocket')}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>
''')
print(f'Project generated: {len(app_sources)} app sources, {len(unit_sources)} unit sources, {len(ui_sources)} UI sources')
