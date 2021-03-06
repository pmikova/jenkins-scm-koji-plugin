import React from "react"
import { useObserver } from "mobx-react"
import { Button, Snackbar, Paper } from "@material-ui/core"

import { Item } from "../../stores/model"
import JDKProjectForm from "./JDKProjectForm"
import TaskForm from "./TaskForm"
import PlatformForm from "./PlatformForm"
import JDKTestProjectForm from "./JDKTestProjectForm"
import { useParams } from "react-router-dom"
import useStores from "../../hooks/useStores"

interface SnackbarState {
    open: boolean
    message?: string
    actions?: JSX.Element[]
}

const ConfigForm: React.FC = () => {

    const { configStore } = useStores()

    const { group, id } = useParams()

    return useObserver(() => {

        const { createConfig, configError, jobUpdateResults, updateConfig, discardOToolResponse } = configStore
        const okButton = (
            <Button
                color="secondary"
                key="ok"
                onClick={() => { discardOToolResponse() }}
                size="small">
                OK
        </Button>
        )

        const snackbarState: SnackbarState | undefined = (configError && {
            open: true,
            message: configError,
            actions: [
                okButton
            ]
        }) || (jobUpdateResults && {
            open: true,
            message: "Done, see console output",
            actions: [
                okButton
            ]
        })

        const onSubmit = async (config: Item) => {
            if (id !== undefined) {
                updateConfig(config)
            } else {
                createConfig(config)
            }
        }

        const renderForm = () => {
            switch (group) {
                case "jdkProjects":
                    return (
                        <JDKProjectForm
                            onSubmit={onSubmit}
                            jdkProjectID={id} />
                    );
                case "jdkTestProjects":
                    return (
                        <JDKTestProjectForm
                            onSubmit={onSubmit}
                            id={id} />
                    )
                case "tasks":
                    return (
                        <TaskForm
                            onSubmit={onSubmit}
                            taskID={id} />
                    );
                case "platforms":
                    return (
                        <PlatformForm
                            onSubmit={onSubmit}
                            platformID={id} />
                    )
                default:
                    return null;
            }
        }

        return (
            <React.Fragment>
                <Paper style={{ padding: 20, width: "100%" }}>
                    {renderForm()}
                </Paper>
                {
                    snackbarState && <Snackbar
                        action={snackbarState.actions}
                        anchorOrigin={{
                            horizontal: "center",
                            vertical: "top"
                        }}
                        autoHideDuration={10000}
                        message={<span>{(snackbarState.message || "").toString()}</span>}
                        open={snackbarState.open} />
                }
            </React.Fragment>
        )
    })
}

export default ConfigForm
