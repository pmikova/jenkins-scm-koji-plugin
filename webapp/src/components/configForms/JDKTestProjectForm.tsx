import React from "react"
import { useLocalStore, useObserver } from "mobx-react-lite"
import { Button } from "@material-ui/core"

import { JDKTestProject, Item } from "../../stores/model"
import TextInput from "../formComponents/TextInput"
import MultiSelect from "../formComponents/MultiSelect"
import JobConfigComponent from "../formComponents/JobConfigComponent";
import Select from "../formComponents/Select";
import useStores from "../../hooks/useStores"

type JDKTestProjectFormProps = {
    id?: string
    onSubmit: (item: Item) => void
}

const JDKTestProjectForm: React.FC<JDKTestProjectFormProps> = props => {

    const { configStore } = useStores()

    const { buildProviders } = configStore

    const { id } = props

    const project = useLocalStore<JDKTestProject>(() => ({
        buildPlatform: "",
        buildProviders: [],
        id: "",
        jobConfiguration: { platforms: {} },
        product: "",
        subpackageBlacklist: [],
        subpackageWhitelist: [],
        type: "JDK_TEST_PROJECT"
    }))

    React.useEffect(() => {
        if (id === undefined || id === project.id) {
            return
        }
        const _jdkProject = configStore.jdkTestProjectMap[id]
        if (!_jdkProject) {
            return
        }
        project.buildProviders = _jdkProject.buildProviders || []
        project.buildPlatform = _jdkProject.buildPlatform || ""
        project.id = _jdkProject.id || ""
        project.jobConfiguration = _jdkProject.jobConfiguration || { platforms: {} }
        project.product = _jdkProject.product || ""
        project.subpackageBlacklist = _jdkProject.subpackageBlacklist
        project.subpackageWhitelist = _jdkProject.subpackageWhitelist
    })

    const onIDChange = (value: string) => {
        project.id = value
    }

    const onBuildProvidersChange = (value: string[]) => {
        project.buildProviders = value
    }

    const onProductChange = (value: string) => {
        project.product = value
    }

    const onBuildPlatformChange = (value: string) => {
        project.buildPlatform = value
    }

    const onSubpackageBlacklistChange = (value: string) => {
        project.subpackageBlacklist = value.split(" ")
    }

    const onSubpackageWhitelistChange = (value: string) => {
        project.subpackageWhitelist = value.split(" ")
    }

    const onSubmit = () => {
        const filter = (value: string) => value.trim() !== ""
        project.subpackageBlacklist = project.subpackageBlacklist.filter(filter)
        project.subpackageWhitelist = project.subpackageWhitelist.filter(filter)
        props.onSubmit(project)
    }

    return useObserver(() =>
        <React.Fragment>
            <TextInput
                label={"name"}
                value={project.id}
                onChange={onIDChange} />
            <MultiSelect
                label={"build providers"}
                onChange={onBuildProvidersChange}
                options={buildProviders.map(buildProvider => buildProvider.id)}
                values={project.buildProviders}
            />
            <TextInput
                label={"build platform"}
                value={project.buildPlatform}
                onChange={onBuildPlatformChange} />
            <Select
                label="product"
                onChange={onProductChange}
                options={configStore.products.map(product => product.id)}
                value={project.product} />
            <TextInput
                label={"subpackage blacklist"}
                value={project.subpackageBlacklist.join(" ")}
                onChange={onSubpackageBlacklistChange} />
            <TextInput
                label={"subpackage whitelist"}
                value={project.subpackageWhitelist.join(" ")}
                onChange={onSubpackageWhitelistChange} />
            <JobConfigComponent
                jobConfig={project.jobConfiguration}
                projectType={project.type}
                 />
            <Button
                onClick={onSubmit}
                variant="contained">
                {id === undefined ? "Create" : "Update"}
            </Button>
        </React.Fragment>
    )
}

export default JDKTestProjectForm
